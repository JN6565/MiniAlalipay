const { createConfig } = require('@umijs/test');
const config = createConfig();

module.exports = {
  ...config,
  moduleNameMapper: {
    ...config.moduleNameMapper,
    '^@/(.*)$': '<rootDir>/src/$1',
  },
};
