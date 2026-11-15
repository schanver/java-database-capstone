export default [
  {
    files: ["app/src/main/resources/static/js/**/*.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: {
        window: "readonly",
        document: "readonly",
        localStorage: "readonly",
        location: "readonly",
        console: "readonly",
        alert: "readonly",
        confirm: "readonly",
        fetch: "readonly",
        URLSearchParams: "readonly",
        setTimeout: "readonly",
        event: "readonly",
      },
    },
    rules: {
      "no-unused-vars": "warn",
      "no-undef": "warn",
      "no-redeclare": "error",
      "no-dupe-keys": "error",
      "no-unreachable": "warn",
      eqeqeq: "warn",
    },
  },
];
