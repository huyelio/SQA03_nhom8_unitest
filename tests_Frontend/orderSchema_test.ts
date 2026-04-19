import { describe, expect, it } from "vitest";
import { orderSchema } from "../src/orderSchema";



describe("orderSchema", () => {
  const validOrderInput = {
    receiverName: "Nguyen Van A",
    receiverPhone: "0912345678",
    shippingAddress: "123 Le Loi, District 1",
  };

  // TC_OS_01: thông tin đơn hàng hợp lệ phải parse thành công.
  it("should parse successfully when order information is valid", () => {
    const parseResult = orderSchema.safeParse(validOrderInput);

    expect(parseResult.success).toBe(true);
  });

  // TC_OS_02: receiverName rỗng phải báo lỗi.
  it("should reject empty receiverName", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, receiverName: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.receiverName).toContain("Vui lòng nhập tên người nhận");
    }
  });

  // TC_OS_03: receiverPhone ngắn hơn 9 ký tự phải báo lỗi.
  it("should reject receiverPhone shorter than 9 characters", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, receiverPhone: "12345678" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.receiverPhone).toContain("Số điện thoại không hợp lệ");
    }
  });

  // TC_OS_04: shippingAddress ngắn hơn 5 ký tự phải báo lỗi.
  it("should reject shippingAddress shorter than 5 characters", () => {
    const parseResult = orderSchema.safeParse({ ...validOrderInput, shippingAddress: "1234" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.shippingAddress).toContain("Vui lòng nhập địa chỉ giao hàng");
    }
  });

  // TC_OS_05: nhiều trường cùng sai phải thu thập nhiều lỗi.
  it("should collect errors for multiple invalid order fields", () => {
    const parseResult = orderSchema.safeParse({
      receiverName: "",
      receiverPhone: "12",
      shippingAddress: "abc",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.receiverName).toContain("Vui lòng nhập tên người nhận");
      expect(fieldErrors.receiverPhone).toContain("Số điện thoại không hợp lệ");
      expect(fieldErrors.shippingAddress).toContain("Vui lòng nhập địa chỉ giao hàng");
    }
  });
});
