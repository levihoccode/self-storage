package vn.lemar.selfstorage.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.pricing.repository.PolicyRepository;

class PolicyQueryTest {

    private final PolicyRepository repository = mock(PolicyRepository.class);
    private final PolicyQuery query = new PolicyQuery(repository);

    @Test
    void parsesIntegerValue() {
        when(repository.findValue("order.expiry_days")).thenReturn(Optional.of(" 30 "));

        assertThat(query.requireInt("order.expiry_days")).isEqualTo(30);
    }

    @Test
    void missingKeyIsConfigurationError() {
        when(repository.findValue("order.expiry_days")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.requireInt("order.expiry_days"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order.expiry_days");
    }

    @Test
    void nonIntegerValueIsConfigurationError() {
        when(repository.findValue("order.expiry_days")).thenReturn(Optional.of("30 ngày"));

        assertThatThrownBy(() -> query.requireInt("order.expiry_days"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không phải số nguyên");
    }
}
