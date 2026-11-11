package com.misaka10843.create_railway_announcer.client;

import com.misaka10843.create_railway_signage.compat.ldlib.LdlibUi;
import com.misaka10843.create_railway_announcer.network.ControllerAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ControllerAudioUi {
    private static Screen pendingParent;
    public static void request(net.minecraft.core.BlockPos pos){pendingParent=Minecraft.getInstance().screen;PacketDistributor.sendToServer(new ControllerAudioPayload.Request(pos));}
    public static void open(ControllerAudioPayload payload){
        Screen parent=pendingParent;pendingParent=null;
        var root=LdlibUi.panel().layout(l->l.paddingAll(7).gapAll(4));
        var status=LdlibUi.text(Component.empty());
        ControllerAnnouncementEditor[] editing={null};
        com.lowdragmc.lowdraglib2.gui.ui.elements.Button[] primary={null};
        Runnable validate=()->{
            if(editing[0]==null||primary[0]==null)return;
            try{editing[0].value();LdlibUi.setPrimaryEnabled(primary[0],payload.editable());status.setText(Component.empty());}
            catch(RuntimeException error){LdlibUi.setPrimaryEnabled(primary[0],false);status.setText(Component.translatable("announcer.create_railway_announcer.invalid"));}
        };
        var editor=new ControllerAnnouncementEditor(payload.settings(),payload.enabled(),payload.admin(),payload.editable(),payload::platforms,validate,payload.pos());editing[0]=editor;
        var save=LdlibUi.primaryButton("announcer.create_railway_announcer.save",event->{
            try {PacketDistributor.sendToServer(new ControllerAudioPayload.Save(payload.pos(),payload.stationId(),editor.value(),editor.globalChange()));}
            catch(RuntimeException error){status.setText(Component.translatable("announcer.create_railway_announcer.invalid"));}
        });
        LdlibUi.setPrimaryEnabled(save,payload.editable());
        primary[0]=save;
        var footer=LdlibUi.row(22);footer.addChildren(LdlibUi.button("gui.done",event->Minecraft.getInstance().setScreen(parent)),LdlibUi.spacer(),save);
        root.addChildren(LdlibUi.heading("announcer.create_railway_announcer.station"),LdlibUi.text(Component.literal(payload.name())),editor.build(),
                LdlibUi.wrappedText("announcer.create_railway_announcer.hint"),status,footer);
        validate.run();
        var ui=com.lowdragmc.lowdraglib2.gui.ui.ModularUI.of(LdlibUi.ui(root,LdlibUi.responsive(.8f,360,700,.9f,260,540)));
        Minecraft.getInstance().setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(ui,Component.translatable("announcer.create_railway_announcer.station")){
            @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
        });
    }
}
