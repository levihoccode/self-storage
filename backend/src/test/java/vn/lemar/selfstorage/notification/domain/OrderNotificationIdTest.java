package vn.lemar.selfstorage.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNotificationIdTest {

    @Test
    void equalsAndHashCodeMatchOnSameValues() {
        OrderNotificationId id = new OrderNotificationId(1L, 2L);
        OrderNotificationId same = new OrderNotificationId(1L, 2L);

        assertThat(id).isEqualTo(id);
        assertThat(id).isEqualTo(same);
        assertThat(id).hasSameHashCodeAs(same);
    }

    @Test
    void differentValuesAreNotEqual() {
        assertThat(new OrderNotificationId(1L, 2L)).isNotEqualTo(new OrderNotificationId(1L, 3L));
        assertThat(new OrderNotificationId(1L, 2L)).isNotEqualTo(new OrderNotificationId(3L, 2L));
    }

    @Test
    void otherTypesAreNotEqual() {
        assertThat(new OrderNotificationId(1L, 2L)).isNotEqualTo(null);
        assertThat(new OrderNotificationId(1L, 2L)).isNotEqualTo("other");
    }
}
