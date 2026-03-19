package com.zahen.playersummonbulk.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.zahen.playersummonbulk.client.gui.PlayerBatchScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PlayerBatchScreen::new;
    }
}
