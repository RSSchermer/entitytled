# Entitytled changelog

## 0.5.0

Replaced the old implementation for eager-loading, which used runtime reflection,
with a new implementation using implicit macros. Entitytled should no longer be
using any runtime reflection (at least according to `-Xlog-reflective-calls`).
 
This *should* not have broken any backwards compatibility. Overriding
`withIncludes` on your entity types is no longer necessary for improved runtime
performance and safety.
