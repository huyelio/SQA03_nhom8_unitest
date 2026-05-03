import AsyncStorage from "@react-native-async-storage/async-storage";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { STORAGE_KEY } from "../constants/common";
import { getToken, removeToken } from "../utils/auth";

vi.mock("@react-native-async-storage/async-storage", () => ({
  default: {
    getItem: vi.fn(),
    removeItem: vi.fn(),
  },
}));

describe("tokenStorage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // TC_TOKEN_01: getToken phải đọc ACCESS_TOKEN từ AsyncStorage và trả về token.
  it("should return token from AsyncStorage when token exists", async () => {
    vi.mocked(AsyncStorage.getItem).mockResolvedValue("access-token-123");

    const result = await getToken();

    expect(AsyncStorage.getItem).toHaveBeenCalledWith(STORAGE_KEY.ACCESS_TOKEN);
    expect(result).toBe("access-token-123");
  });

  // TC_TOKEN_02: getToken phải trả về null khi AsyncStorage không có token.
  it("should return null when AsyncStorage has no token", async () => {
    vi.mocked(AsyncStorage.getItem).mockResolvedValue(null);

    const result = await getToken();

    expect(AsyncStorage.getItem).toHaveBeenCalledWith(STORAGE_KEY.ACCESS_TOKEN);
    expect(result).toBe(null);
  });

  // TC_TOKEN_03: getToken phải catch lỗi, log lỗi và trả về null.
  it("should log error and return null when AsyncStorage.getItem throws", async () => {
    const error = new Error("Read failed");
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.mocked(AsyncStorage.getItem).mockRejectedValue(error);

    const result = await getToken();

    expect(result).toBe(null);
    expect(consoleSpy).toHaveBeenCalledWith("Error reading token:", error);
  });

  // TC_TOKEN_04: removeToken phải xóa ACCESS_TOKEN khỏi AsyncStorage.
  it("should remove access token from AsyncStorage", async () => {
    vi.mocked(AsyncStorage.removeItem).mockResolvedValue(undefined);

    await removeToken();

    expect(AsyncStorage.removeItem).toHaveBeenCalledWith(STORAGE_KEY.ACCESS_TOKEN);
  });

  // TC_TOKEN_05: removeToken phải catch và log lỗi khi AsyncStorage.removeItem throw.
  it("should log error when AsyncStorage.removeItem throws", async () => {
    const error = new Error("Remove failed");
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.mocked(AsyncStorage.removeItem).mockRejectedValue(error);

    await removeToken();

    expect(consoleSpy).toHaveBeenCalledWith("Error removing token:", error);
  });
});
