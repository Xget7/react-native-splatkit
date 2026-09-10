import { expect, test } from '@jest/globals';
import { act, create } from 'react-test-renderer';
import App from '../App';

// The point of this test is not the HUD; it is that a consumer's Jest, with
// the React Native preset, can import the package's build and mount the view.
test('the example renders with the native view mounted', async () => {
  let tree!: ReturnType<typeof create>;
  await act(async () => {
    tree = create(<App />);
  });
  const json = JSON.stringify(tree.toJSON());
  expect(json).toContain('"type":"SplatView"');
});
