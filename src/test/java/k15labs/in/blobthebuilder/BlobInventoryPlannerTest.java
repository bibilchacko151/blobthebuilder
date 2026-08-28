package k15labs.in.blobthebuilder;

import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobInventoryPlannerTest {
    private final BlobInventoryPlanner planner = new BlobInventoryPlanner();

    @Test
    void zeroByteParentWithChildrenIsDirectoryMarker() {
        List<BlobInventoryPlanner.PlannedBlob> plan = planner.plan(List.of(
            blob("root/PNR/99", 0),
            blob("root/PNR/99/timestamp/metadata.json", 12)
        ));

        assertEquals(BlobInventoryPlanner.Kind.DIRECTORY_MARKER, plan.get(0).kind());
        assertTrue(plan.get(0).hasChildren());
        assertEquals(BlobInventoryPlanner.Kind.FILE, plan.get(1).kind());
    }

    @Test
    void genuineZeroByteFileWithoutChildrenRemainsAFile() {
        BlobInventoryPlanner.PlannedBlob emptyFile = planner.plan(List.of(blob("root/PNR/empty.txt", 0))).get(0);

        assertEquals(BlobInventoryPlanner.Kind.FILE, emptyFile.kind());
        assertFalse(emptyFile.hasChildren());
    }

    @Test
    void nonZeroParentWithChildrenIsNamespaceConflict() {
        List<BlobInventoryPlanner.PlannedBlob> plan = planner.plan(List.of(
            blob("root/PNR/item", 5),
            blob("root/PNR/item/timestamp/data.json", 10)
        ));

        assertEquals(BlobInventoryPlanner.Kind.NAMESPACE_CONFLICT, plan.get(0).kind());
        assertEquals(BlobInventoryPlanner.Kind.FILE, plan.get(1).kind());
    }

    @Test
    void trailingSlashEntryIsDirectoryMarker() {
        BlobInventoryPlanner.PlannedBlob marker = planner.plan(List.of(blob("root/PNR/folder/", 0))).get(0);

        assertEquals(BlobInventoryPlanner.Kind.DIRECTORY_MARKER, marker.kind());
        assertEquals("root/PNR/folder", marker.logicalPath());
    }

    @Test
    void hdiFolderMetadataIsDirectoryMarker() {
        BlobItem marker = blob("root/PNR/folder", 17).setMetadata(Map.of("Hdi_IsFolder", "TRUE"));

        assertEquals(BlobInventoryPlanner.Kind.DIRECTORY_MARKER, planner.plan(List.of(marker)).get(0).kind());
    }

    @Test
    void nestedFileWithoutMatchingParentBlobRemainsNormalFile() {
        BlobInventoryPlanner.PlannedBlob nested = planner.plan(List.of(
            blob("root/PNR/1/timestamp/details/metadata.json", 20)
        )).get(0);

        assertEquals(BlobInventoryPlanner.Kind.FILE, nested.kind());
    }

    private BlobItem blob(String name, long contentLength) {
        return new BlobItem()
            .setName(name)
            .setProperties(new BlobItemProperties().setContentLength(contentLength));
    }
}
