module.exports = {
  preset: '@react-native/jest-preset',
  // The package is resolved from the workspace source, the same way
  // metro.config.js does it through the react-native-splatkit-source condition.
  // react and react-native are pinned to this app's copies so the source, which
  // lives one level up, does not pull a second React from the root.
  moduleNameMapper: {
    '^react-native-splatkit$': '<rootDir>/../src/index.tsx',
    '^react$': '<rootDir>/node_modules/react',
    '^react-native$': '<rootDir>/node_modules/react-native',
  },
};
