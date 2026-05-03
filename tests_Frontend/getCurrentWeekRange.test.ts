import { afterEach, describe, expect, it, vi } from "vitest";
import { getCurrentWeekRange } from "../utils/getCurrentWeekRange";

describe("getCurrentWeekRange", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  // TC_WEEK_01: Ngày giữa tuần phải trả về thứ 2 và chủ nhật của cùng ISO week.
  it("should return Monday and Sunday when current date is in the middle of week", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 3, 29, 12, 0, 0));

    const result = getCurrentWeekRange();

    expect(result).toEqual({ start_date: "2026-04-27", end_date: "2026-05-03" });
  });

  // TC_WEEK_02: Nếu ngày hiện tại là thứ 2 thì start_date là chính ngày đó.
  it("should use current day as start_date when current date is Monday", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 3, 27, 12, 0, 0));

    const result = getCurrentWeekRange();

    expect(result).toEqual({ start_date: "2026-04-27", end_date: "2026-05-03" });
  });

  // TC_WEEK_03: Nếu ngày hiện tại là chủ nhật thì end_date là chính ngày đó.
  it("should use current day as end_date when current date is Sunday", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 4, 3, 12, 0, 0));

    const result = getCurrentWeekRange();

    expect(result).toEqual({ start_date: "2026-04-27", end_date: "2026-05-03" });
  });

  // TC_WEEK_04: Tuần ISO bắc qua năm mới phải format đúng YYYY-MM-DD.
  it("should return correct range when ISO week crosses year boundary", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2025, 11, 31, 12, 0, 0));

    const result = getCurrentWeekRange();

    expect(result).toEqual({ start_date: "2025-12-29", end_date: "2026-01-04" });
  });
});
