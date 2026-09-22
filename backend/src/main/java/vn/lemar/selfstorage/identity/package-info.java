/**
 * Tài khoản, xác thực và phân quyền RBAC.
 *
 * <p>Module nền, không phụ thuộc ngược lại module nghiệp vụ nào (issue #15 mục 1).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity", allowedDependencies = {})
package vn.lemar.selfstorage.identity;
