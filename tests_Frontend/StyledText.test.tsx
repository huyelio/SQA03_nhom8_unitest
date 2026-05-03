import * as React from "react";
import renderer from "react-test-renderer";
import { describe, expect, it, vi } from "vitest";

vi.mock("react-native", async () => {
  const React = await import("react");

  return {
    Text: ({ children, ...props }: any) =>
      React.createElement("Text", props, children),

    View: ({ children, ...props }: any) =>
      React.createElement("View", props, children),

    StyleSheet: {
      create: (styles: any) => styles,
      flatten: (style: any) => style,
    },

    Platform: {
      OS: "ios",
      select: (options: any) => options.ios ?? options.default,
    },

    useColorScheme: () => "light",
  };
});

vi.mock("../components/useColorScheme", () => ({
  useColorScheme: () => "light",
}));

vi.mock("../components/useColorScheme.web", () => ({
  useColorScheme: () => "light",
}));

import { MonoText } from "../components/StyledText";

describe("MonoText", () => {
  // TC_ST_01: MonoText render đúng snapshot với nội dung text truyền vào.
  it("should render MonoText correctly", () => {
    const tree = renderer.create(<MonoText>Snapshot test!</MonoText>).toJSON();

    expect(tree).toMatchSnapshot();
  });
});