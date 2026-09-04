import type { ColorValue, ViewProps } from 'react-native';

type Props = ViewProps & {
  color?: ColorValue;
};

export function SplatkitView(_props: Props): never {
  throw new Error(
    "'react-native-splatkit' is only supported on native platforms."
  );
}
