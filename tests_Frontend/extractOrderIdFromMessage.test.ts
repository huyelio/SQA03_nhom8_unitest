import { describe, expect, it } from "vitest";
import { extractOrderIdFromMessage } from "../utils/getIDFormMSG";

describe("extractOrderIdFromMessage", () => {
  // TC_EXTRACT_01: Message chứa Order ID chuẩn phải trả về number.
  it("should return order id number when message contains standard Order ID format", () => {
    const result = extractOrderIdFromMessage("Payment success. Order ID: 12345");

    expect(result).toBe(12345);
  });

  // TC_EXTRACT_02: Regex phải không phân biệt hoa thường.
  it("should extract order id case-insensitively", () => {
    const result = extractOrderIdFromMessage("order id: 456");

    expect(result).toBe(456);
  });

  // TC_EXTRACT_03: Có nhiều khoảng trắng sau dấu hai chấm vẫn phải lấy được ID.
  it("should extract order id when there are extra spaces after colon", () => {
    const result = extractOrderIdFromMessage("Order ID:     789");

    expect(result).toBe(789);
  });

  // TC_EXTRACT_04: ID có số 0 ở đầu phải convert thành number đúng.
  it("should convert leading zero order id to number", () => {
    const result = extractOrderIdFromMessage("Order ID: 000123");

    expect(result).toBe(123);
  });

  // TC_EXTRACT_05: Message có nhiều Order ID phải lấy ID đầu tiên theo regex hiện tại.
  it("should return first order id when message contains multiple ids", () => {
    const result = extractOrderIdFromMessage("Order ID: 111, previous Order ID: 222");

    expect(result).toBe(111);
  });

  // TC_EXTRACT_06: Message không có Order ID phải trả về null.
  it("should return null when message does not contain order id", () => {
    const result = extractOrderIdFromMessage("Payment success without id");

    expect(result).toBe(null);
  });

  // TC_EXTRACT_07: Order ID không phải số phải trả về null.
  it("should return null when order id value is not numeric", () => {
    const result = extractOrderIdFromMessage("Order ID: ABC");

    expect(result).toBe(null);
  });

  // TC_EXTRACT_08: Chuỗi rỗng phải trả về null.
  it("should return null when message is empty", () => {
    const result = extractOrderIdFromMessage("");

    expect(result).toBe(null);
  });
});
