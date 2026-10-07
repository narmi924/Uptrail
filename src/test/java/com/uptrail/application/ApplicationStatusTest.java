package com.uptrail.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.uptrail.model.ApplicationStatus;

/**
 * Every legal and illegal transition of the application state machine
 * (see docs/diagrams/application-states.puml).
 */
class ApplicationStatusTest {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> LEGAL = Map.of(
            ApplicationStatus.APPLIED, EnumSet.of(ApplicationStatus.UPDATED, ApplicationStatus.DELETED,
                    ApplicationStatus.APPROVED, ApplicationStatus.REJECTED),
            ApplicationStatus.UPDATED, EnumSet.of(ApplicationStatus.UPDATED, ApplicationStatus.DELETED,
                    ApplicationStatus.APPROVED, ApplicationStatus.REJECTED),
            ApplicationStatus.APPROVED, EnumSet.of(ApplicationStatus.CANCELLED, ApplicationStatus.COMPLETED),
            ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.DELETED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.CANCELLED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.COMPLETED, EnumSet.noneOf(ApplicationStatus.class));

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void onlyTheDiagramTransitionsAreAllowed(ApplicationStatus from) {
        for (ApplicationStatus to : ApplicationStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(LEGAL.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void terminalStatesHaveNoWayOut(ApplicationStatus status) {
        assertThat(status.isTerminal()).isEqualTo(LEGAL.get(status).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void onlyActiveApplicationsBlockOverlappingPeriods(ApplicationStatus status) {
        boolean blocking = status == ApplicationStatus.APPLIED || status == ApplicationStatus.UPDATED
                || status == ApplicationStatus.APPROVED;
        assertThat(ApplicationStatus.OVERLAP_BLOCKING.contains(status)).isEqualTo(blocking);
    }
}
