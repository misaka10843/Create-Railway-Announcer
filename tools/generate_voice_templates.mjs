import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'../src/main/resources/assets/create_railway_announcer/default_packs/jr_like_default');
const events={
 onboard_next_stop_dynamic:['onboard_next_stop','ONBOARD_VOICE',70],onboard_approaching_dynamic:['onboard_approaching','ONBOARD_VOICE',80],
 onboard_arrived_dynamic:['onboard_arrived','ONBOARD_VOICE',80],onboard_departing_dynamic:['onboard_departing','ONBOARD_VOICE',60],
 onboard_terminal_dynamic:['onboard_terminal','ONBOARD_VOICE',90],onboard_door_opening_dynamic:['door_opening','ONBOARD_VOICE',90],
 onboard_door_closing_dynamic:['door_closing','ONBOARD_VOICE',90],onboard_safety_dynamic:['safety_notice','ONBOARD_VOICE',70],
 platform_approach_dynamic:['platform_approach','PLATFORM_VOICE',80],platform_arrival_dynamic:['platform_arrival','PLATFORM_VOICE',80],
 platform_pre_departure_dynamic:['platform_pre_departure','PLATFORM_VOICE',85],platform_door_closing_dynamic:['platform_door_closing','PLATFORM_VOICE',90],
 platform_departed_dynamic:['platform_departed','PLATFORM_VOICE',70]
};
const texts={
ja_jp:['次は、{station_reading}、{station_reading}です。','まもなく、{station_reading}です。','{station_reading}、{station_reading}です。','ご乗車ありがとうございます。','終点、{station_reading}です。お忘れ物のないよう、ご注意ください。','ドアが開きます。足元にご注意ください。','ドアが閉まります。ご注意ください。','車内では、周りのお客様へのご配慮をお願いいたします。','まもなく、{platform}番線に電車がまいります。危ないですから、黄色い線の内側までお下がりください。','{platform}番線に電車が到着しました。お降りのお客様を先にお通しください。','{platform}番線の電車は、まもなく発車します。','{platform}番線の電車のドアが閉まります。ご注意ください。','{platform}番線の電車が発車しました。'],
en_us:['The next stop is {station_reading}.','We will soon arrive at {station_reading}.','This is {station_reading}.','Thank you for travelling with us.','This train terminates at {station_reading}. Please take all your belongings.','The doors are opening. Please watch your step.','The doors are closing. Please stand clear.','Please be considerate of other passengers.','A train is approaching platform {platform}. Please stand behind the yellow line.','A train has arrived at platform {platform}. Please allow passengers to alight first.','The train at platform {platform} will depart shortly.','The doors of the train at platform {platform} are closing. Please stand clear.','The train at platform {platform} has departed.'],
zh_cn:['下一站，{station_reading}。','列车即将到达{station_reading}。','{station_reading}到了。','感谢您乘坐本次列车。','终点站{station_reading}到了。请带好随身物品。','车门即将开启，请注意脚下安全。','车门正在关闭，请勿靠近车门。','请照顾身边的乘客，文明乘车。','列车即将进入{platform}站台，请站在黄色安全线以内。','列车已到达{platform}站台，请先下后上。','{platform}站台的列车即将发车。','{platform}站台的列车正在关闭车门，请注意安全。','{platform}站台的列车已经发车。']
};
const languages={ja_jp:'ja-JP',en_us:'en-US',zh_cn:'zh-CN'};
const phrases={ja_jp:['お出口は左側です。','お出口は右側です。','お出口は両側です。','はお乗り換えです。'],en_us:['The doors will open on the left.','The doors will open on the right.','The doors will open on both sides.','services are available for transfer.'],zh_cn:['请从左侧车门下车。','请从右侧车门下车。','两侧车门均可下车。','可以在本站换乘。']};
function emit(file,value){const target=path.join(root,file),data=JSON.stringify(value,null,2)+'\n';if(process.argv.includes('--check')){if(!fs.existsSync(target)||fs.readFileSync(target,'utf8')!==data)throw Error('Voice template drift: '+file);}else{fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,data);}}
for(const [language,messages] of Object.entries(texts)){
 const templates={};let index=0;
 for(const [id,[event,channel,priority]]of Object.entries(events)){
  const text=messages[index++];const ssml=`<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="${languages[language]}"><voice name="{voice}"><prosody rate="{rate}" pitch="{pitch}">${text}</prosody></voice></speak>`;
  const sequence=[{type:'subtitle',text},{type:'tts_raw',ssml:true,text:ssml}];
  if(['onboard_next_stop','onboard_approaching','onboard_arrived'].includes(event))sequence.push({type:'door_side_phrase'});
  if(event==='onboard_next_stop')sequence.push({type:'condition',when:{has_transfers:true},then:[{type:'pause',duration_ms:300},{type:'transfer_lines',pause_ms_between:180,include_suffix:true}]});
  templates[id]={event,channel,priority,sequence};
 }
 templates.platform_melody_dynamic={event:'platform_departure_melody',channel:'MELODY',priority:60,sequence:[{type:'sound',file:'departure.ogg'}]};
 for(const [id,event]of [['door_open_chime','door_open_chime'],['door_close_chime','door_close_chime']])templates[id]={event,channel:'DOOR_CHIME',priority:100,sequence:[{type:'sound',file:'door_bip.ogg'}]};
 emit(`templates/${language}.json`,templates);
 emit(`phrases/${language}.json`,Object.fromEntries(['common.door_left','common.door_right','common.door_both','common.transfer_suffix'].map((id,i)=>[id,phrases[language][i]])));
}
emit('pack.json',{pack_format:1,id:'jr_like_default',name:'Railway Default Pack',description:'Original speech templates and a simple original departure melody; no official railway recordings.',author:'Create Railway Announcer',version:'0.2.0',default_language:'ja_jp',supported_languages:Object.keys(languages)});
console.log('PASS: built-in voice templates and phrases for three languages');
