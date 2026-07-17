package com.misaka10843.createrailwayannouncer.compat.create;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;

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