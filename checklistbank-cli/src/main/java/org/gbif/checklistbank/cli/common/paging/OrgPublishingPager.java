package org.gbif.checklistbank.cli.common.paging;

import org.gbif.api.model.common.paging.PagingRequest;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.service.registry.OrganizationService;
import org.gbif.api.vocabulary.DatasetType;

import java.util.UUID;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterates over all datasets published by a given organisation.
 */
public class OrgPublishingPager extends DatasetBasePager {
    private static final Logger LOG = LoggerFactory.getLogger(OrgPublishingPager.class);

    private final OrganizationService os;
    private final UUID orgKey;

    public OrgPublishingPager(OrganizationService os, UUID orgKey, @Nullable DatasetType type) {
        super(type);
        this.os = os;
        this.orgKey = orgKey;
    }

    @Override
    PagingResponse<Dataset> nextPage(PagingRequest page) {
        return os.publishedDatasets(orgKey, page);
    }

}
