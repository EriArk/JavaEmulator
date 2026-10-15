package ru.playsoftware.j2meloader.appsdb;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import ru.playsoftware.j2meloader.applist.AppItem;

/** An explicit user choice, not a claim that two provider URIs identify the same document. */
@Entity(tableName = "source_aliases", foreignKeys = @ForeignKey(entity = AppItem.class,
        parentColumns = "id", childColumns = "appId", onDelete = ForeignKey.CASCADE),
        indices = @Index("appId"))
public class SourceAlias {
    @PrimaryKey @NonNull public String sourceKey;
    @NonNull public String fingerprint;
    public int appId;

    public SourceAlias(@NonNull String sourceKey, @NonNull String fingerprint, int appId) {
        this.sourceKey=sourceKey; this.fingerprint=fingerprint; this.appId=appId;
    }
}
