package vn.lemar.selfstorage.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingDomainTest {

    @ParameterizedTest
    @EnumSource(ProposalStatus.class)
    void proposalStatusRoundTripsThroughConverter(ProposalStatus status) {
        ProposalStatusConverter converter = new ProposalStatusConverter();

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(status))).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(RentalOrderStatus.class)
    void rentalOrderStatusRoundTripsThroughConverter(RentalOrderStatus status) {
        RentalOrderStatusConverter converter = new RentalOrderStatusConverter();

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(status))).isEqualTo(status);
    }

    @Test
    void convertersPassNullThrough() {
        assertThat(new ProposalStatusConverter().convertToDatabaseColumn(null)).isNull();
        assertThat(new ProposalStatusConverter().convertToEntityAttribute(null)).isNull();
        assertThat(new RentalOrderStatusConverter().convertToDatabaseColumn(null)).isNull();
        assertThat(new RentalOrderStatusConverter().convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unknownDbValuesAreRejected() {
        assertThatThrownBy(() -> ProposalStatus.fromDbValue("PENDING")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RentalOrderStatus.fromDbValue("pending"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyCanceledAndDoneOrdersAreClosed() {
        assertThat(RentalOrderStatus.values()).filteredOn(RentalOrderStatus::isClosed)
                .containsExactlyInAnyOrder(RentalOrderStatus.CANCELED, RentalOrderStatus.DONE);
    }

    @Test
    void handledProposalCannotChangeStateAgain() {
        ProposalFeedback proposal = ProposalFeedback.propose(new RentalOrder(), 7L);
        proposal.agree();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AGREED);
        assertThatThrownBy(proposal::agree).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> proposal.reject("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(proposal::expire).isInstanceOf(IllegalStateException.class);
    }
}
