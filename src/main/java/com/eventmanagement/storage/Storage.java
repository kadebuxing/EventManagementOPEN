package com.eventmanagement.storage;

import com.eventmanagement.model.Store;

/** Persistence boundary used by the service layer. */
public interface Storage {
    Store load();

    void save(Store store);
}
