package k15labs.in.blobthebuilder;

import com.azure.storage.blob.models.BlobItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BlobInventoryPlanner {

    enum Kind {
        FILE,
        DIRECTORY_MARKER,
        NAMESPACE_CONFLICT,
        UNCLASSIFIABLE_PARENT
    }

    record PlannedBlob(BlobItem item, String blobName, String logicalPath, boolean hasChildren, Kind kind) {
    }

    List<PlannedBlob> plan(List<BlobItem> blobItems) {
        Set<String> parentPaths = discoverParentPaths(blobItems);
        List<PlannedBlob> planned = new ArrayList<>(blobItems.size());

        for (BlobItem item : blobItems) {
            String name = requireName(item);
            String logicalPath = stripTrailingSlashes(name);
            boolean hasChildren = parentPaths.contains(logicalPath);
            planned.add(new PlannedBlob(item, name, logicalPath, hasChildren, classify(item, name, hasChildren)));
        }
        return List.copyOf(planned);
    }

    private Set<String> discoverParentPaths(List<BlobItem> blobItems) {
        Set<String> parentPaths = new HashSet<>();
        for (BlobItem item : blobItems) {
            String name = requireName(item);
            for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                if (slash > 0) {
                    parentPaths.add(name.substring(0, slash));
                }
            }
        }
        return parentPaths;
    }

    private Kind classify(BlobItem item, String name, boolean hasChildren) {
        if (name.endsWith("/") || metadataMarksFolder(item.getMetadata())) {
            return Kind.DIRECTORY_MARKER;
        }
        if (!hasChildren) {
            return Kind.FILE;
        }

        Long contentLength = item.getProperties() == null ? null : item.getProperties().getContentLength();
        if (Long.valueOf(0L).equals(contentLength)) {
            return Kind.DIRECTORY_MARKER;
        }
        if (contentLength != null && contentLength > 0) {
            return Kind.NAMESPACE_CONFLICT;
        }
        return Kind.UNCLASSIFIABLE_PARENT;
    }

    private boolean metadataMarksFolder(Map<String, String> metadata) {
        if (metadata == null) {
            return false;
        }
        return metadata.entrySet().stream().anyMatch(entry ->
            "hdi_isfolder".equalsIgnoreCase(entry.getKey()) && "true".equalsIgnoreCase(entry.getValue())
        );
    }

    private String requireName(BlobItem item) {
        if (item == null || item.getName() == null || item.getName().isBlank()) {
            throw new IllegalArgumentException("Blob listing contained an item with no name");
        }
        return item.getName();
    }

    private String stripTrailingSlashes(String name) {
        int end = name.length();
        while (end > 0 && name.charAt(end - 1) == '/') {
            end--;
        }
        if (end == 0) {
            throw new IllegalArgumentException("Invalid directory marker path: " + name);
        }
        return name.substring(0, end);
    }
}
