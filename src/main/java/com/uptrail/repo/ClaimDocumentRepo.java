package com.uptrail.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.uptrail.model.ClaimDocument;

public interface ClaimDocumentRepo extends JpaRepository<ClaimDocument, Long> {

    List<ClaimDocument> findByClaimIdOrderByClaimRevisionDescDocumentTypeAsc(Long claimId);

    /** Every stored key, for the orphan sweep. */
    @Query("select d.storageKey from ClaimDocument d")
    List<String> findAllStorageKeys();
}
