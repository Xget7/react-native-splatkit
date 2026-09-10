/** Pure unit tests for the JavaScript side; nothing here imports react-native. */
module.exports = {
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  transform: { '\\.[jt]sx?$': 'babel-jest' },
  modulePathIgnorePatterns: ['<rootDir>/lib/', '<rootDir>/example/'],
};
