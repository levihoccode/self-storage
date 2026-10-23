package vn.lemar.selfstorage.identity.application;

/**
 * Account đã xác minh email ({@code email_verified_at} khác null) — điều kiện để liên kết/claim
 * {@code RentalRequest} ({@code specs/db-table-draft.md} › Account).
 *
 * @param email email đã chuẩn hoá (trim + lowercase)
 */
public record VerifiedAccount(Long id, String email) {
}
