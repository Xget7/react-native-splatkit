#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * The iOS half of the component.
 *
 * The engine is not here yet. `splatkit-android` is a published artifact this
 * package links against, and iOS is waiting on the same thing: a SplatKit
 * package it can link rather than a renderer copied into this repo.
 *
 * Until then this view exists so the component, its props and its events are
 * identical on both platforms. It reports `onEngineReady` with `available:
 * false`, which is the same signal an Android device without Vulkan gives, so an
 * app that already handles that case handles iOS with no extra branch.
 */
@interface SplatView : RCTViewComponentView
@end

NS_ASSUME_NONNULL_END
