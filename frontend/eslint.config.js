import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";

// Cổng lint tối thiểu nhưng có thật: typescript-eslint recommended + react-hooks.
// Không nhồi rule nặng — phải thực tế với code hiện tại và các nhánh fe/feat/*.
const reactHooksRules =
  reactHooks.configs.flat?.recommended?.rules ??
  reactHooks.configs["recommended-latest"]?.rules ??
  reactHooks.configs.recommended.rules;

export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    plugins: { "react-hooks": reactHooks },
    rules: { ...reactHooksRules },
  },
);
