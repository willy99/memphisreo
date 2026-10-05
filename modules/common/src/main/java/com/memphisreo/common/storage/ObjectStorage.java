package com.memphisreo.common.storage;

import java.io.InputStream;

/**
 * S3-сумісний object storage — MinIO зараз (self-host), AWS S3 пізніше без
 * зміни коду модулів, що це використовують (document, згодом media).
 * docs/architecture.md §7.
 */
public interface ObjectStorage {

    /** @return ключ об'єкта в сховищі (не публічний URL — доступ через {@link #get}). */
    String put(String key, InputStream content, long contentLength, String contentType);

    InputStream get(String key);

    void delete(String key);
}
