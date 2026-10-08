package com.memphisreo.document;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.storage.ObjectStorage;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Самодостатній сервіс модуля document. Перевірка видимості (лістер +
 * юрист) — на рівні platform-app, бо потребує Property з іншого модуля —
 * docs/domain-model.md §6.
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ObjectStorage objectStorage;

    public DocumentService(DocumentRepository documentRepository, ObjectStorage objectStorage) {
        this.documentRepository = documentRepository;
        this.objectStorage = objectStorage;
    }

    public Document upload(UUID tenantId, UUID propertyId, UUID uploadedByAgentId, Document.Type type,
                            Document.Visibility visibility, String fileName, String contentType,
                            InputStream content, long contentLength) {
        // Префікс tenants/<id>/ — видалення агенції = видалення префікса (ADR-001).
        // Ім'я файлу — лише в БД, не в ключі (довільні символи від клієнта).
        String objectKey = "tenants/%s/properties/%s/documents/%s".formatted(tenantId, propertyId, UUID.randomUUID());
        objectStorage.put(objectKey, content, contentLength, contentType);

        Document document = new Document();
        document.setTenantId(tenantId);
        document.setPropertyId(propertyId);
        document.setUploadedByAgentId(uploadedByAgentId);
        document.setType(type);
        document.setVisibility(visibility);
        document.setObjectKey(objectKey);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setFileSizeBytes(contentLength);

        return documentRepository.save(document);
    }

    public List<Document> listByProperty(UUID propertyId) {
        return documentRepository.findByPropertyId(propertyId);
    }

    public Document get(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document не знайдено: " + id));
    }

    public InputStream download(Document document) {
        return objectStorage.get(document.getObjectKey());
    }

    public void delete(Document document) {
        objectStorage.delete(document.getObjectKey());
        documentRepository.delete(document);
    }
}
