import { describe, expect, it } from "vitest";
import { LoginSchema } from "../src/loginSchema";



describe("LoginSchema", () => {
  const validLoginInput = {
    email: "user@example.com",
    password: "Password123",
  };

  // TC_LS_01: Dữ liệu đăng nhập hợp lệ phải parse thành công.
  it("should parse successfully when email and password are valid", () => {
    const parseResult = LoginSchema.safeParse(validLoginInput);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data).toEqual(validLoginInput);
    }
  });

  // TC_LS_02: email rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when email is empty", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, email: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.email).toContain("Email không được bỏ trống");
    }
  });

  // TC_LS_03: email sai định dạng phải báo lỗi.
  it("should return invalid error when email format is incorrect", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, email: "user-at-example.com" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.email).toContain("Email không hợp lệ");
    }
  });

  // TC_LS_04: password rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when password is empty", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, password: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.password).toContain("Mật khẩu không được bỏ trống");
    }
  });

  // TC_LS_05: password ngắn hơn 8 ký tự phải báo lỗi.
  it("should return min-length error when password is shorter than 8 characters", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, password: "1234567" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.password).toContain("Mật khẩu ít nhất 8 ký tự");
    }
  });

  // TC_LS_06: email và password cùng sai phải thu thập lỗi cả hai trường.
  it("should collect errors for both email and password when both fields are invalid", () => {
    const parseResult = LoginSchema.safeParse({ email: "wrong", password: "123" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.email).toContain("Email không hợp lệ");
      expect(fieldErrors.password).toContain("Mật khẩu ít nhất 8 ký tự");
    }
  });
});
