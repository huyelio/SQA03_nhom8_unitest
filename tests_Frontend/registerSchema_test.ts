import { describe, expect, it } from "vitest";
import { RegisterSchema } from "../schema/RegisterSchema";

const expectFieldError = (parseResult: ReturnType<typeof RegisterSchema.safeParse>, field: string, message?: string) => {
  expect(parseResult.success).toBe(false);
  if (!parseResult.success) {
    const fieldErrors = parseResult.error.flatten().fieldErrors as Record<string, string[] | undefined>;
    expect(fieldErrors[field]).toBeTruthy();
    if (message) {
      expect(fieldErrors[field]).toContain(message);
    }
  }
};

describe("RegisterSchema", () => {
  const validRegisterInput = {
    email: "user@example.com",
    password: "Password123",
    name: "Nguyễn Văn A",
    dateOfBirth: "2000-01-01",
    gender: "male",
    checkPassword: "Password123",
  };

  // TC_REG_01: Dữ liệu đăng ký hợp lệ phải parse thành công.
  it("should parse successfully when all register fields are valid", () => {
    const parseResult = RegisterSchema.safeParse(validRegisterInput);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data).toEqual(validRegisterInput);
    }
  });

  // TC_REG_02: name rỗng phải báo lỗi bắt buộc nhập tên.
  it("should return required error when name is empty", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, name: "" });

    expectFieldError(parseResult, "name", "Hãy nhập tên");
  });

  // TC_REG_03: dateOfBirth rỗng phải báo lỗi bắt buộc nhập ngày sinh.
  it("should return required error when dateOfBirth is empty", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, dateOfBirth: "" });

    expectFieldError(parseResult, "dateOfBirth", "Hãy nhập ngày sinh");
  });

  // TC_REG_04: gender rỗng phải báo lỗi bắt buộc chọn giới tính.
  it("should return required error when gender is empty", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, gender: "" });

    expectFieldError(parseResult, "gender", "Hãy chọn giới tính");
  });

  // TC_REG_05: checkPassword khác password phải báo lỗi xác nhận mật khẩu.
  it("should return mismatch error when checkPassword is different from password", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, checkPassword: "Different123" });

    expectFieldError(parseResult, "checkPassword", "Mật khẩu xác nhận không khớp");
  });

  // TC_REG_06: checkPassword rỗng phải báo lỗi xác nhận mật khẩu khi password có giá trị.
  it("should return mismatch error when checkPassword is empty", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, checkPassword: "" });

    expectFieldError(parseResult, "checkPassword", "Mật khẩu xác nhận không khớp");
  });

  // TC_REG_07: RegisterSchema phải kế thừa validate email từ LoginSchema.
  it("should return email error inherited from LoginSchema when email is invalid", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, email: "invalid-email" });

    expectFieldError(parseResult, "email", "Email không hợp lệ");
  });

  // TC_REG_08: RegisterSchema phải kế thừa validate password từ LoginSchema.
  it("should return password min length error inherited from LoginSchema", () => {
    const parseResult = RegisterSchema.safeParse({
      ...validRegisterInput,
      password: "1234567",
      checkPassword: "1234567",
    });

    expectFieldError(parseResult, "password", "Mật khẩu ít nhất 8 ký tự");
  });

  // TC_REG_09: Nhiều trường đăng ký sai cùng lúc phải được gom lỗi theo field.
  it("should collect errors for multiple invalid register fields", () => {
    const parseResult = RegisterSchema.safeParse({
      email: "bad-email",
      password: "123",
      name: "",
      dateOfBirth: "",
      gender: "",
      checkPassword: "456",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.email).toBeTruthy();
      expect(fieldErrors.password).toBeTruthy();
      expect(fieldErrors.name).toBeTruthy();
      expect(fieldErrors.dateOfBirth).toBeTruthy();
      expect(fieldErrors.gender).toBeTruthy();
      expect(fieldErrors.checkPassword).toBeTruthy();
    }
  });
});
