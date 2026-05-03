import { describe, expect, it } from "vitest";
import { orderSchema } from "../schema/orderSchema";

const expectFieldError = (
  parseResult: ReturnType<typeof orderSchema.safeParse>,
  field: string,
  message?: string
) => {
  expect(parseResult.success).toBe(false);
  if (!parseResult.success) {
    const fieldErrors = parseResult.error.flatten().fieldErrors as Record<string, string[] | undefined>;
    expect(fieldErrors[field]).toBeTruthy();
    if (message) {
      expect(fieldErrors[field]).toContain(message);
    }
  }
};

describe("orderSchema", () => {
  const validOrderInput = {
    receiverName: "Nguyễn Văn A",
    receiverPhone: "0912345678",
    shippingAddress: "123 Nguyễn Trãi, Quận 1",
  };

  // TC_ORDER_01: Dữ liệu đặt hàng hợp lệ phải parse thành công.
  it("should parse successfully when order input is valid", () => {
    const parseResult = orderSchema.safeParse(validOrderInput);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data).toEqual(validOrderInput);
    }
  });

  // TC_ORDER_02: receiverName rỗng phải báo lỗi bắt buộc nhập tên người nhận.
  it("should return required error when receiverName is empty", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, receiverName: "" });

    expectFieldError(parseResult, "receiverName", "Vui lòng nhập tên người nhận");
  });

  // TC_ORDER_03: receiverPhone ngắn hơn 9 ký tự phải báo lỗi.
  it("should reject receiverPhone shorter than 9 characters", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, receiverPhone: "12345678" });

    expectFieldError(parseResult, "receiverPhone", "Số điện thoại không hợp lệ");
  });

  // TC_ORDER_04: receiverPhone đúng ranh giới 9 ký tự phải hợp lệ.
  it("should accept receiverPhone at minimum boundary length 9", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, receiverPhone: "123456789" });

    expect(parseResult.success).toBe(true);
  });

  // TC_ORDER_05: shippingAddress rỗng phải báo lỗi địa chỉ giao hàng.
  it("should return address error when shippingAddress is empty", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, shippingAddress: "" });

    expectFieldError(parseResult, "shippingAddress", "Vui lòng nhập địa chỉ giao hàng");
  });

  // TC_ORDER_06: shippingAddress ngắn hơn 5 ký tự phải báo lỗi.
  it("should reject shippingAddress shorter than 5 characters", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, shippingAddress: "1234" });

    expectFieldError(parseResult, "shippingAddress", "Vui lòng nhập địa chỉ giao hàng");
  });

  // TC_ORDER_07: Nhiều trường đặt hàng sai cùng lúc phải gom lỗi theo field.
  it("should collect errors for multiple invalid order fields", () => {
    const parseResult = orderSchema.safeParse({
      receiverName: "",
      receiverPhone: "123",
      shippingAddress: "",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.receiverName).toBeTruthy();
      expect(fieldErrors.receiverPhone).toBeTruthy();
      expect(fieldErrors.shippingAddress).toBeTruthy();
    }
  });
});