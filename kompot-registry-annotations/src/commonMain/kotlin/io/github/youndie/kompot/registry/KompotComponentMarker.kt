package io.github.youndie.kompot.registry

// Marks a class for auto-discovery by :kompot-registry-processor (KSP). One annotation with two
// independent uses, told apart by which interface the marked class implements:
// - on a component data class (implements KompotComponent) — generates a polymorphic registration
//   entry in
//   Generated<Tag>SerializersModule.
// - on a renderer class (implements KompotComponentRenderer<T>) — generates an entry in
//   Generated<Tag>Renderers, where T is derived by the processor from the renderer's own generic
//   argument rather than from this annotation.
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompotComponentMarker
