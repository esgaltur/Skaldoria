## Reactive Slide Synchronization

Here is how our project manager streams multi-file updates to the reactive UI:

```kotlin [1-3|5-8|10-12]
val project = DeckProjectManager.loadProject(manifestFile)
val combined = project.compileCombinedMarkdown()
val ast = MarkdownSlideParser.parse(combined)

fun onSlideFileModified(file: SlideFileEntry) {
    project.updateFile(file)
    state.notifyStateChanged()
}

// Zero allocation during presentation loop
canvas.drawSlide(currentSlide, transitionMatrix)
```

<!-- note: Explain that parsing happens asynchronously in background dispatchers while Compose Desktop renders at 120 FPS. -->
