package com.uptrail.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.uptrail.service.AccessScopePolicy;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.support.AbstractMySqlIT;
import com.uptrail.support.Fixtures.Person;

/**
 * Data-scope checks: owners, current direct managers, assigned approvers and past reviewers.
 */
class AccessScopePolicyIT extends AbstractMySqlIT {

    @Autowired
    private AccessScopePolicy policy;

    private Person owner;
    private Person otherEmployee;
    private Person manager;
    private Person otherManager;

    @BeforeEach
    void setUp() {
        owner = fixtures.employee("owner");
        otherEmployee = fixtures.employee("other");
        manager = fixtures.manager("boss");
        otherManager = fixtures.manager("elsewhere");
        fixtures.route(owner, manager);
        fixtures.route(otherEmployee, otherManager);
    }

    @Test
    void onlyTheOwnerPassesTheOwnerCheck() {
        assertThatCode(() -> policy.requireOwner(owner.actor(), owner.id())).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireOwner(otherEmployee.actor(), owner.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void currentDirectManagerIsRecognised() {
        assertThat(policy.isCurrentDirectManager(manager.actor(), owner.id())).isTrue();
        assertThat(policy.isCurrentDirectManager(otherManager.actor(), owner.id())).isFalse();
        assertThat(policy.isCurrentDirectManager(otherEmployee.actor(), owner.id())).isFalse();
        assertThatThrownBy(() -> policy.requireCurrentDirectManager(otherManager.actor(), owner.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void managersSeeRecordsTheyApproveOrDecidedEvenAfterReorganisation() {
        assertThat(policy.canManagerView(otherManager.actor(), owner.id(), otherManager.id(), null)).isTrue();
        assertThat(policy.canManagerView(otherManager.actor(), owner.id(), manager.id(), otherManager.id())).isTrue();
        assertThat(policy.canManagerView(otherManager.actor(), owner.id(), manager.id(), null)).isFalse();
    }

    @Test
    void nobodyReviewsTheirOwnRecordAsManager() {
        assertThat(policy.canManagerView(manager.actor(), manager.id(), manager.id(), null)).isFalse();
        assertThat(policy.canManagerView(owner.actor(), owner.id(), owner.id(), null)).isFalse();
    }
}
