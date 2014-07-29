package org.gbif.checklistbank.cli.postal;

import org.gbif.common.messaging.api.messages.DatasetBasedMessage;

import java.util.UUID;

import com.google.common.base.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The message sent whenever an entire checklist is imported into checklistbank.
 */
public class ChecklistSyncedMessage implements DatasetBasedMessage {

  private final UUID datasetUuid;

  @JsonCreator
  public ChecklistSyncedMessage(
    @JsonProperty("datasetUuid") UUID datasetUuid
  ) {
    this.datasetUuid = checkNotNull(datasetUuid, "datasetUuid can't be null");
  }

  @Override
  public String getRoutingKey() {
    return "checklist.imported";
  }

  @Override
  public UUID getDatasetUuid() {
    return datasetUuid;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(datasetUuid);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ChecklistSyncedMessage other = (ChecklistSyncedMessage) obj;
    return Objects.equal(this.datasetUuid, other.datasetUuid);
  }
}
