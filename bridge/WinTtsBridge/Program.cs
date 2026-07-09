using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Speech.Synthesis;
using Windows.Media.SpeechSynthesis;
using Windows.Storage.Streams;
using WinRtSynth = Windows.Media.SpeechSynthesis.SpeechSynthesizer;
using WinRtVoiceInformation = Windows.Media.SpeechSynthesis.VoiceInformation;
using SapiVoiceInfo = System.Speech.Synthesis.VoiceInfo;

namespace WinTtsBridge;

internal static class Program
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    public static async Task<int> Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        Console.InputEncoding = Encoding.UTF8;

        try
        {
            if (args.Length == 0 || Has(args, "--help") || Has(args, "-h"))
            {
                PrintHelp();
                return 0;
            }

            var command = args[0].ToLowerInvariant();
            var options = ParseOptions(args.Skip(1).ToArray());

            return command switch
            {
                "list-voices" => ListVoices(options),
                "synth" => await Synthesize(options),
                "probe" => Probe(options),
                _ => Fail($"Unknown command: {args[0]}")
            };
        }
        catch (Exception ex)
        {
            WriteJson(new ErrorResult(false, "exception", ex.Message, ex.ToString()));
            return 10;
        }
    }

    private static int ListVoices(Dictionary<string, string?> options)
    {
        var backend = NormalizeBackend(Get(options, "backend") ?? "auto");
        var language = Get(options, "language") ?? Get(options, "lang");
        var contains = Get(options, "contains");
        var naturalOnly = GetBool(options, "natural-only", false);

        var voices = new List<VoiceDto>();
        if (backend is "winrt" or "auto" or "all") voices.AddRange(ListWinRtVoices());
        if (backend is "sapi" or "auto" or "all") voices.AddRange(ListSapiVoices());

        voices = voices
            .Where(v => string.IsNullOrWhiteSpace(language) || v.Language.Equals(language, StringComparison.OrdinalIgnoreCase))
            .Where(v => string.IsNullOrWhiteSpace(contains)
                        || v.DisplayName.Contains(contains, StringComparison.OrdinalIgnoreCase)
                        || v.Id.Contains(contains, StringComparison.OrdinalIgnoreCase)
                        || (v.Description?.Contains(contains, StringComparison.OrdinalIgnoreCase) ?? false))
            .Where(v => !naturalOnly || v.IsProbablyNatural)
            .OrderByDescending(v => v.IsProbablyNatural)
            .ThenBy(v => v.Backend == "sapi" ? 0 : 1)
            .ThenBy(v => v.Language)
            .ThenBy(v => v.DisplayName)
            .ToList();

        WriteJson(new ListVoicesResult(true, backend, voices.Count, voices));
        return 0;
    }

    private static int Probe(Dictionary<string, string?> options)
    {
        var backend = NormalizeBackend(Get(options, "backend") ?? "auto");
        var preferredLanguage = Get(options, "language") ?? Get(options, "lang") ?? "ja-JP";
        var preferred = SelectVoiceDto(options, preferredLanguage, backend, allowDefault: false);
        var defaultVoice = GetDefaultVoiceDto(backend);
        var count = CountLanguageVoices(preferredLanguage, backend);

        WriteJson(new ProbeResult(
            true,
            backend,
            RuntimeInformation(),
            defaultVoice,
            preferred,
            count
        ));
        return 0;
    }

    private static async Task<int> Synthesize(Dictionary<string, string?> options)
    {
        var backend = NormalizeBackend(Get(options, "backend") ?? "auto");
        var outPath = Required(options, "out");
        var language = Get(options, "language") ?? Get(options, "lang") ?? "ja-JP";
        var isSsml = options.ContainsKey("ssml-file") || GetBool(options, "ssml", false);

        string input;
        if (options.TryGetValue("text-file", out var textFile) && !string.IsNullOrWhiteSpace(textFile))
        {
            input = await File.ReadAllTextAsync(textFile, Encoding.UTF8);
        }
        else if (options.TryGetValue("ssml-file", out var ssmlFile) && !string.IsNullOrWhiteSpace(ssmlFile))
        {
            input = await File.ReadAllTextAsync(ssmlFile, Encoding.UTF8);
            isSsml = true;
        }
        else if (options.TryGetValue("text", out var text) && !string.IsNullOrWhiteSpace(text))
        {
            input = text;
        }
        else
        {
            return Fail("Missing input. Use --text-file, --ssml-file or --text.");
        }

        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(outPath)) ?? ".");

        var selected = SelectVoiceDto(options, language, backend, allowDefault: true);
        if (selected is null)
        {
            return Fail($"No usable voice found for backend={backend}, language={language}.");
        }

        if (selected.Backend == "sapi")
        {
            SynthesizeSapi(input, outPath, selected, options, isSsml);
        }
        else
        {
            await SynthesizeWinRt(input, outPath, selected, options, isSsml);
        }

        var fileInfo = new FileInfo(outPath);
        WriteJson(new SynthesizeResult(
            true,
            selected.Backend,
            Path.GetFullPath(outPath),
            fileInfo.Exists ? fileInfo.Length : 0,
            selected,
            isSsml ? "ssml" : "text"
        ));
        return 0;
    }

    private static List<VoiceDto> ListWinRtVoices()
    {
        return WinRtSynth.AllVoices.Select(ToVoiceDto).ToList();
    }

    private static List<VoiceDto> ListSapiVoices()
    {
        using var synth = new System.Speech.Synthesis.SpeechSynthesizer();
        return synth.GetInstalledVoices()
            .Where(v => v.Enabled)
            .Select(v => ToVoiceDto(v.VoiceInfo))
            .ToList();
    }

    private static VoiceDto? GetDefaultVoiceDto(string backend)
    {
        try
        {
            if (backend == "sapi")
            {
                using var synth = new System.Speech.Synthesis.SpeechSynthesizer();
                return ToVoiceDto(synth.Voice);
            }

            if (backend == "winrt") return ToVoiceDto(WinRtSynth.DefaultVoice);

            return SelectVoiceDto(new Dictionary<string, string?>(), "ja-JP", "auto", allowDefault: true);
        }
        catch
        {
            return null;
        }
    }

    private static int CountLanguageVoices(string language, string backend)
    {
        var count = 0;
        if (backend is "winrt" or "auto" or "all")
        {
            count += ListWinRtVoices().Count(v => v.Language.Equals(language, StringComparison.OrdinalIgnoreCase));
        }
        if (backend is "sapi" or "auto" or "all")
        {
            count += ListSapiVoices().Count(v => v.Language.Equals(language, StringComparison.OrdinalIgnoreCase));
        }
        return count;
    }

    private static VoiceDto? SelectVoiceDto(Dictionary<string, string?> options, string language, string backend, bool allowDefault)
    {
        var voiceId = Get(options, "voice-id") ?? Get(options, "voice");
        var contains = Get(options, "voice-contains") ?? Get(options, "contains");
        var naturalPreferred = GetBool(options, "natural-preferred", true);
        var naturalOnly = GetBool(options, "natural-only", false);

        var candidates = new List<VoiceDto>();
        if (backend is "sapi" or "auto" or "all") candidates.AddRange(ListSapiVoices());
        if (backend is "winrt" or "auto" or "all") candidates.AddRange(ListWinRtVoices());

        if (!string.IsNullOrWhiteSpace(voiceId))
        {
            var byId = candidates.FirstOrDefault(v => v.Id.Equals(voiceId, StringComparison.OrdinalIgnoreCase))
                       ?? candidates.FirstOrDefault(v => v.DisplayName.Equals(voiceId, StringComparison.OrdinalIgnoreCase));
            if (byId is not null) return byId;
        }

        if (!string.IsNullOrWhiteSpace(contains))
        {
            var byContains = candidates
                .Where(v => v.Language.Equals(language, StringComparison.OrdinalIgnoreCase))
                .Where(v => ContainsVoice(v, contains))
                .Where(v => !naturalOnly || v.IsProbablyNatural)
                .OrderByDescending(v => v.IsProbablyNatural)
                .ThenBy(v => v.Backend == "sapi" ? 0 : 1)
                .FirstOrDefault();
            if (byContains is not null) return byContains;
        }

        var languageCandidates = candidates
            .Where(v => v.Language.Equals(language, StringComparison.OrdinalIgnoreCase))
            .Where(v => !naturalOnly || v.IsProbablyNatural)
            .OrderByDescending(v => naturalPreferred && v.IsProbablyNatural)
            .ThenBy(v => v.Backend == "sapi" ? 0 : 1)
            .ThenBy(v => v.Gender.Equals("Female", StringComparison.OrdinalIgnoreCase) ? 0 : 1)
            .ThenBy(v => v.DisplayName)
            .ToList();

        if (languageCandidates.Count > 0) return languageCandidates[0];

        if (!allowDefault) return null;
        if (backend == "sapi")
        {
            using var synth = new System.Speech.Synthesis.SpeechSynthesizer();
            return ToVoiceDto(synth.Voice);
        }
        if (backend == "winrt") return ToVoiceDto(WinRtSynth.DefaultVoice);

        return candidates.OrderByDescending(v => v.IsProbablyNatural).ThenBy(v => v.Backend == "sapi" ? 0 : 1).FirstOrDefault();
    }

    private static async Task SynthesizeWinRt(string input, string outPath, VoiceDto selected, Dictionary<string, string?> options, bool isSsml)
    {
        using var synthesizer = new WinRtSynth();
        var voice = WinRtSynth.AllVoices.FirstOrDefault(v => v.Id.Equals(selected.Id, StringComparison.OrdinalIgnoreCase))
                    ?? WinRtSynth.AllVoices.FirstOrDefault(v => v.DisplayName.Equals(selected.DisplayName, StringComparison.OrdinalIgnoreCase));
        if (voice is not null) synthesizer.Voice = voice;
        ApplyWinRtOptions(synthesizer, options);

        SpeechSynthesisStream stream = isSsml
            ? await synthesizer.SynthesizeSsmlToStreamAsync(input).AsTask()
            : await synthesizer.SynthesizeTextToStreamAsync(input).AsTask();

        await SaveWinRtStream(stream, outPath);
    }

    private static void SynthesizeSapi(string input, string outPath, VoiceDto selected, Dictionary<string, string?> options, bool isSsml)
    {
        using var synth = new System.Speech.Synthesis.SpeechSynthesizer();
        synth.SelectVoice(selected.DisplayName);
        ApplySapiOptions(synth, options);
        synth.SetOutputToWaveFile(outPath);
        if (isSsml)
        {
            synth.SpeakSsml(input);
        }
        else
        {
            synth.Speak(input);
        }
        synth.SetOutputToNull();
    }

    private static void ApplyWinRtOptions(WinRtSynth synthesizer, Dictionary<string, string?> options)
    {
        if (TryGetDouble(options, "rate", out var rate))
        {
            synthesizer.Options.SpeakingRate = Clamp(rate, 0.5, 6.0);
        }

        if (TryGetDouble(options, "volume", out var volume))
        {
            synthesizer.Options.AudioVolume = Clamp(volume > 1.0 ? volume / 100.0 : volume, 0.0, 1.0);
        }

        if (TryGetDouble(options, "pitch", out var pitch))
        {
            synthesizer.Options.AudioPitch = Clamp(pitch, 0.0, 2.0);
        }
    }

    private static void ApplySapiOptions(System.Speech.Synthesis.SpeechSynthesizer synth, Dictionary<string, string?> options)
    {
        if (TryGetDouble(options, "rate", out var rate))
        {
            // Accept SAPI style -10..10, or WinRT-like 0.5..2.0. For railway use, 0.9 becomes about -1.
            synth.Rate = rate is >= -10 and <= 10 && Math.Abs(rate) > 2.0
                ? (int)Math.Round(rate)
                : (int)Math.Round(Clamp((rate - 1.0) * 10.0, -10, 10));
        }

        if (TryGetDouble(options, "volume", out var volume))
        {
            synth.Volume = (int)Math.Round(Clamp(volume > 1.0 ? volume : volume * 100.0, 0, 100));
        }
    }

    private static bool ContainsVoice(VoiceDto v, string value)
    {
        return v.Id.Contains(value, StringComparison.OrdinalIgnoreCase)
               || v.DisplayName.Contains(value, StringComparison.OrdinalIgnoreCase)
               || (v.Description?.Contains(value, StringComparison.OrdinalIgnoreCase) ?? false)
               || (v.Provider?.Contains(value, StringComparison.OrdinalIgnoreCase) ?? false);
    }

    private static VoiceDto ToVoiceDto(WinRtVoiceInformation v)
    {
        return new VoiceDto(
            "winrt",
            v.Id,
            v.DisplayName,
            v.Language,
            v.Gender.ToString(),
            v.Description,
            null,
            IsProbablyNatural(v.Id, v.DisplayName, v.Description)
        );
    }

    private static VoiceDto ToVoiceDto(SapiVoiceInfo v)
    {
        return new VoiceDto(
            "sapi",
            v.Id,
            v.Name,
            v.Culture.Name,
            v.Gender.ToString(),
            v.Description,
            v.AdditionalInfo.TryGetValue("Vendor", out var vendor) ? vendor : null,
            IsProbablyNatural(v.Id, v.Name, v.Description)
        );
    }

    private static bool IsProbablyNatural(string id, string displayName, string? description)
    {
        return displayName.Contains("Natural", StringComparison.OrdinalIgnoreCase)
               || id.Contains("Natural", StringComparison.OrdinalIgnoreCase)
               || (description?.Contains("Natural", StringComparison.OrdinalIgnoreCase) ?? false)
               || displayName.Contains("Nanami", StringComparison.OrdinalIgnoreCase)
               || displayName.Contains("Keita", StringComparison.OrdinalIgnoreCase)
               || displayName.Contains("AvaMultilingual", StringComparison.OrdinalIgnoreCase)
               || displayName.Contains("AndrewMultilingual", StringComparison.OrdinalIgnoreCase);
    }

    private static async Task SaveWinRtStream(SpeechSynthesisStream input, string outPath)
    {
        await using var file = new FileStream(outPath, FileMode.Create, FileAccess.Write, FileShare.Read);
        using var reader = new DataReader(input.GetInputStreamAt(0));
        const uint chunkSize = 64 * 1024;

        while (true)
        {
            var loaded = await reader.LoadAsync(chunkSize).AsTask();
            if (loaded == 0) break;
            var buffer = new byte[loaded];
            reader.ReadBytes(buffer);
            await file.WriteAsync(buffer);
        }
    }

    private static string NormalizeBackend(string backend)
    {
        backend = backend.Trim().ToLowerInvariant();
        return backend switch
        {
            "windows" or "system" or "auto" => "auto",
            "all" => "all",
            "winrt" or "onecore" => "winrt",
            "sapi" or "sapi5" => "sapi",
            _ => "auto"
        };
    }

    private static string RuntimeInformation()
    {
        return $"{Environment.OSVersion}; {System.Runtime.InteropServices.RuntimeInformation.FrameworkDescription}; {System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture}";
    }

    private static Dictionary<string, string?> ParseOptions(string[] args)
    {
        var dict = new Dictionary<string, string?>(StringComparer.OrdinalIgnoreCase);
        for (var i = 0; i < args.Length; i++)
        {
            var arg = args[i];
            if (!arg.StartsWith("--", StringComparison.Ordinal)) continue;

            var key = arg[2..];
            if (key.Contains('='))
            {
                var parts = key.Split('=', 2);
                dict[parts[0]] = parts[1];
                continue;
            }

            if (i + 1 < args.Length && !args[i + 1].StartsWith("--", StringComparison.Ordinal))
            {
                dict[key] = args[++i];
            }
            else
            {
                dict[key] = "true";
            }
        }
        return dict;
    }

    private static string Required(Dictionary<string, string?> options, string key)
    {
        var value = Get(options, key);
        if (string.IsNullOrWhiteSpace(value)) throw new ArgumentException($"Missing required option --{key}.");
        return value;
    }

    private static string? Get(Dictionary<string, string?> options, string key)
    {
        return options.TryGetValue(key, out var value) ? value : null;
    }

    private static bool GetBool(Dictionary<string, string?> options, string key, bool defaultValue)
    {
        if (!options.TryGetValue(key, out var value)) return defaultValue;
        return value is null || value.Equals("true", StringComparison.OrdinalIgnoreCase) || value == "1" || value.Equals("yes", StringComparison.OrdinalIgnoreCase);
    }

    private static bool TryGetDouble(Dictionary<string, string?> options, string key, out double value)
    {
        value = 0;
        return options.TryGetValue(key, out var raw)
               && double.TryParse(raw, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out value);
    }

    private static double Clamp(double value, double min, double max) => Math.Min(max, Math.Max(min, value));
    private static bool Has(string[] args, string name) => args.Any(a => a.Equals(name, StringComparison.OrdinalIgnoreCase));

    private static int Fail(string message)
    {
        WriteJson(new ErrorResult(false, "error", message, null));
        return 2;
    }

    private static void WriteJson<T>(T value)
    {
        Console.WriteLine(JsonSerializer.Serialize(value, JsonOptions));
    }

    private static void PrintHelp()
    {
        Console.WriteLine("""
WinTtsBridge - Windows TTS bridge for Minecraft/Java clients

Commands:
  list-voices [--backend auto|winrt|sapi|all] [--language ja-JP] [--contains Nanami] [--natural-only]
  probe [--backend auto|winrt|sapi] [--language ja-JP] [--contains Nanami]
  synth --backend auto|winrt|sapi --text-file input.txt --out output.wav [--language ja-JP] [--voice-contains Nanami] [--rate 0.92] [--volume 95]
  synth --backend auto|winrt|sapi --ssml-file input.ssml --out output.wav [--language ja-JP] [--voice-contains Nanami]

Backends:
  winrt  Windows.Media.SpeechSynthesis / OneCore voices.
  sapi   System.Speech / SAPI5 voices. This can use NaturalVoiceSAPIAdapter voices when installed.
  auto   Prefer SAPI natural-like voices, then fallback to WinRT/OneCore voices.

Notes:
  - Use --text-file or --ssml-file instead of putting long text on the command line.
  - Natural Narrator voices are usually not exposed through WinRT AllVoices directly.
  - For Nanami/Keita Natural voices, install NaturalVoiceSAPIAdapter and use --backend sapi or auto.
""");
    }
}

internal sealed record VoiceDto(string Backend, string Id, string DisplayName, string Language, string Gender, string? Description, string? Provider, bool IsProbablyNatural);
internal sealed record ListVoicesResult(bool Ok, string Backend, int Count, IReadOnlyList<VoiceDto> Voices);
internal sealed record ProbeResult(bool Ok, string Backend, string Runtime, VoiceDto? DefaultVoice, VoiceDto? PreferredVoice, int PreferredLanguageVoiceCount);
internal sealed record SynthesizeResult(bool Ok, string Backend, string Output, long Bytes, VoiceDto Voice, string InputKind);
internal sealed record ErrorResult(bool Ok, string Type, string Message, string? Detail);
