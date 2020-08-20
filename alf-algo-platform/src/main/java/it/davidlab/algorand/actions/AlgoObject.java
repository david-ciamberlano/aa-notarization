package it.davidlab.algorand.actions;

import java.util.Date;

public class AlgoObject {

    private final String sha256hexContent;
    private final String documentName;
    private final String creator;
    private final Date created;
    private final String internalUid;

    public AlgoObject(final String sha256hexContent, final String documentName, final String creator,
                      final Date created, final String internalUid) {
        this.sha256hexContent = sha256hexContent;
        this.documentName = documentName;
        this.creator = creator;
        this.created = created;
        this.internalUid = internalUid;
    }

    public String getSha256hexContent() {
        return this.sha256hexContent;
    }

    public String getDocumentName() {
        return this.documentName;
    }

    public String getCreator() {
        return this.creator;
    }

    public Date getCreated() {
        return this.created;
    }

    public String getInternalUid() {
        return this.internalUid;
    }
}
