package vn.lemar.selfstorage.payment.application.dto;

/**
 * Phản hồi trả về cho VNPay khi nhận IPN.
 *
 * <p>Mã trả về quyết định VNPay có gọi lại hay không: {@code 00} và {@code 02} là chốt, các mã
 * còn lại khiến VNPay retry tối đa 10 lần, mỗi lần cách 5 phút.
 */
public record IpnResponse(String rspCode, String message) {

    public static IpnResponse success() {
        return new IpnResponse("00", "Confirm Success");
    }

    /** Giao dịch đã được xử lý trước đó — đây là nhánh chặn IPN lặp. */
    public static IpnResponse alreadyConfirmed() {
        return new IpnResponse("02", "Order already confirmed");
    }

    public static IpnResponse orderNotFound() {
        return new IpnResponse("01", "Order not found");
    }

    public static IpnResponse invalidAmount() {
        return new IpnResponse("04", "Invalid amount");
    }

    public static IpnResponse invalidSignature() {
        return new IpnResponse("97", "Invalid signature");
    }

    public static IpnResponse unknownError() {
        return new IpnResponse("99", "Unknown error");
    }
}
