import { describe, expect, it } from "vitest";
import { LoginSchema } from "../schema/loginSchema";

const expectFieldError = (parseResult: ReturnType<typeof LoginSchema.safeParse>, field: string, message?: string) => {
  expect(parseResult.success).toBe(false);
  if (!parseResult.success) {
    const fieldErrors = parseResult.error.flatten().fieldErrors as Record<string, string[] | undefined>;
    expect(fieldErrors[field]).toBeTruthy();
    if (message) {
      expect(fieldErrors[field]).toContain(message);
    }
  }
};

describe("LoginSchema", () => {
  const validLoginInput = {
    email: "user@example.com",
    password: "Password123",
  };

  // TC_LOGIN_01: Dữ liệu đăng nhập hợp lệ phải được parse thành công.
  it("should parse successfully when email and password are valid", () => {
    const parseResult = LoginSchema.safeParse(validLoginInput);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data).toEqual(validLoginInput);
    }
  });

  // TC_LOGIN_02: Email rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when email is empty", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, email: "" });

    expectFieldError(parseResult, "email", "Email không được bỏ trống");
  });

  // TC_LOGIN_03: Email sai định dạng phải báo lỗi email không hợp lệ.
  it("should return invalid email error when email format is invalid", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, email: "invalid-email" });

    expectFieldError(parseResult, "email", "Email không hợp lệ");
  });

  // TC_LOGIN_04: Password rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when password is empty", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, password: "" });

    expectFieldError(parseResult, "password", "Mật khẩu không được bỏ trống");
  });

  // TC_LOGIN_05: Password ngắn hơn 8 ký tự phải báo lỗi độ dài tối thiểu.
  it("should return min length error when password has fewer than 8 characters", () => {
    const parseResult = LoginSchema.safeParse({ ...validLoginInput, password: "1234567" });

    expectFieldError(parseResult, "password", "Mật khẩu ít nhất 8 ký tự");
  });

  // TC_LOGIN_06: Nhiều trường sai cùng lúc phải trả lỗi cho cả email và password.
  it("should collect errors for invalid email and password at the same time", () => {
    const parseResult = LoginSchema.safeParse({ email: "bad-email", password: "123" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.email).toBeTruthy();
      expect(fieldErrors.password).toBeTruthy();
    }
  });
});
