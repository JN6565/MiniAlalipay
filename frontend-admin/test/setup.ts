/**
 * Jest 测试全局初始化（setupFilesAfterEnv）。
 *
 * 引入 @testing-library/jest-dom 的匹配器扩展（如 toBeInTheDocument），
 * 使测试断言在 jsdom 环境中可读。后续如需注入全局 mock，应优先放这里而不是各测试文件。
 */
import '@testing-library/jest-dom';
