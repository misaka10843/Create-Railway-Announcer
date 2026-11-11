package com.misaka10843.create_railway_announcer.tts;

import java.io.StringReader;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class Ssml {
    private Ssml() {}

    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public static String document(TtsRequest request, String pitch) {
        if (request.ssml()) {
            validate(request.input());
            return request.input();
        }
        String result = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\""
                + escape(request.language()) + "\"><voice name=\"" + escape(request.voiceContains())
                + "\"><prosody rate=\"" + String.format(Locale.ROOT, "%+.0f%%", (request.rate() - 1) * 100)
                + "\" pitch=\"" + escape(pitch) + "\" volume=\"" + request.volume() + "\">"
                + escape(request.input()) + "</prosody></voice></speak>";
        validate(result);
        return result;
    }

    public static void validate(String value) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var root = builder.parse(new InputSource(new StringReader(value))).getDocumentElement();
            if (!"speak".equals(root.getLocalName()) || !"http://www.w3.org/2001/10/synthesis".equals(root.getNamespaceURI())) {
                throw new IllegalArgumentException("SSML requires a speak root in the synthesis namespace");
            }
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid SSML", error);
        }
    }
}
