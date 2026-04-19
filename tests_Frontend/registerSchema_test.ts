import { describe, expect, it } from "vitest";
import { RegisterSchema } from "../src/registerSchema";



describe("RegisterSchema", () => {
  const validRegisterInput = {
    name: "Tran Quang Huy",
    dateOfBirth: "2004-07-10",
    gender: "male",
    email: "huy@example.com",
    password: "Password123",
    checkPassword: "Password123",
  };

  // TC_RS_01: dữ liệu đăng ký hợp lệ phải parse thành công.
  it("should parse successfully when registration data is valid", () => {
    const parseResult = RegisterSchema.safeParse(validRegisterInput);

    expect(parseResult.success).toBe(true);
  });

  // TC_RS_02: name rỗng phải báo lỗi.
  it("should reject empty name", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, name: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.name).toContain("Hãy nhập tên");
    }
  });

  // TC_RS_03: dateOfBirth rỗng phải báo lỗi.
  it("should reject empty dateOfBirth", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, dateOfBirth: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.dateOfBirth).toContain("Hãy nhập ngày sinh");
    }
  });

  // TC_RS_04: gender rỗng phải báo lỗi.
  it("should reject empty gender", () => {
    const parseResult = RegisterSchema.safeParse({ ...validRegisterInput, gender: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.gender).toContain("Hãy chọn giới tính");
    }
  });

  // TC_RS_05: checkPassword không khớp password phải báo lỗi refine.
  it("should reject mismatched confirmation password", () => {
    const parseResult = RegisterSchema.safeParse({
      ...validRegisterInput,
      checkPassword: "DifferentPassword123",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.checkPassword).toContain("Mật khẩu xác nhận không khớp");
    }
  });

  // TC_RS_06: schema đăng ký phải kế thừa validate email từ LoginSchema.
  it("should reuse login email validation in registration schema", () => {
    const parseResult = RegisterSchema.safeParse({
      ...validRegisterInput,
      email: "invalid-email",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.email).toContain("Email không hợp lệ");
    }
  });

  // TC_RS_07: schema đăng ký phải kế thừa validate password từ LoginSchema.
  it("should reuse login password validation in registration schema", () => {
    const parseResult = RegisterSchema.safeParse({
      ...validRegisterInput,
      password: "1234567",
      checkPassword: "1234567",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.password).toContain("Mật khẩu ít nhất 8 ký tự");
    }
  });

  // TC_RS_08: nhiều trường cùng sai phải thu thập nhiều lỗi.
  it("should collect multiple registration field errors in one parse", () => {
    const parseResult = RegisterSchema.safeParse({
      name: "",
      dateOfBirth: "",
      gender: "",
      email: "wrong",
      password: "123",
      checkPassword: "456",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.name).toContain("Hãy nhập tên");
      expect(fieldErrors.dateOfBirth).toContain("Hãy nhập ngày sinh");
      expect(fieldErrors.gender).toContain("Hãy chọn giới tính");
      expect(fieldErrors.email).toContain("Email không hợp lệ");
      expect(fieldErrors.password).toContain("Mật khẩu ít nhất 8 ký tự");
      expect(fieldErrors.checkPassword).toContain("Mật khẩu xác nhận không khớp");
    }
  });
});
