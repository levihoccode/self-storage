package vn.lemar.selfstorage.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.pricing.repository.PricingRepository;

class PricingQueryTest {

    private final PricingRepository repository = mock(PricingRepository.class);
    private final PricingQuery query = new PricingQuery(repository);

    @Test
    void parsesIntegerPolicy() {
        when(repository.findPolicyValue("invoice.deposit_months")).thenReturn(Optional.of(" 2 "));

        assertThat(query.intPolicy("invoice.deposit_months")).isEqualTo(2);
    }

    @Test
    void missingPolicyIsAConfigurationError() {
        when(repository.findPolicyValue("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.intPolicy("x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policies.x");
    }

    @Test
    void returnsEffectivePriceOrFailsForUnknownUnit() {
        when(repository.findEffectiveMonthlyPrice(1L)).thenReturn(Optional.of(new BigDecimal("500000.00")));
        when(repository.findEffectiveMonthlyPrice(2L)).thenReturn(Optional.empty());

        assertThat(query.effectiveMonthlyPrice(1L)).isEqualByComparingTo("500000");
        assertThatThrownBy(() -> query.effectiveMonthlyPrice(2L)).isInstanceOf(IllegalStateException.class);
    }
}
