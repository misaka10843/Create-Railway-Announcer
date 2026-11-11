package com.misaka10843.create_railway_announcer.compat.create;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;

public final class CreateTrainAdapterHolder {

    private static CreateTrainAdapter adapter =
            new Create6TrainAdapter();

    private CreateTrainAdapterHolder() {
    }

    public static CreateTrainAdapter get() {
        return adapter;
    }

    public static void set(CreateTrainAdapter newAdapter) {
        adapter = newAdapter;

        CreateRailwayAnnouncer.LOGGER.info(
                "Create train adapter changed: {}",
                newAdapter.getClass().getName()
        );
    }
}