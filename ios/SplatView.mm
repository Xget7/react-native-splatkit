#import "SplatView.h"

#import <react/renderer/components/SplatViewSpec/ComponentDescriptors.h>
#import <react/renderer/components/SplatViewSpec/EventEmitters.h>
#import <react/renderer/components/SplatViewSpec/Props.h>
#import <react/renderer/components/SplatViewSpec/RCTComponentViewHelpers.h>

#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

@interface SplatView () <RCTSplatViewViewProtocol>
@end

@implementation SplatView {
  BOOL _announced;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<SplatViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const SplatViewProps>();
    _props = defaultProps;
    self.contentView = [[UIView alloc] initWithFrame:self.bounds];
  }
  return self;
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  if (self.window == nil || _announced) {
    return;
  }
  _announced = YES;
  if (auto emitter = std::static_pointer_cast<const SplatViewEventEmitter>(_eventEmitter)) {
    emitter->onEngineReady({.available = false, .gpu = ""});
  }
}

- (void)prepareForRecycle
{
  _announced = NO;
  [super prepareForRecycle];
}

// Commands exist so the JavaScript API is the same shape on both platforms.
// With no engine to drive, they do nothing.
- (void)setWalkVelocity:(double)forward right:(double)right {}
- (void)startBenchmark:(double)seconds {}

@end

Class<RCTComponentViewProtocol> SplatViewCls(void)
{
  return SplatView.class;
}
