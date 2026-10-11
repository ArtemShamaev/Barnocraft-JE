# Текстуры Адского леса

Созданы встроенным инструментом `imagegen` (без CLI/API fallback). Готовые PNG скопированы в стандартную папку `src/imgs` и включаются Maven в `Textures/` внутри JAR. Исходные изображения сохранены без перезаписи существующих текстур. Для блока используется весь квадрат, фильтрация — nearest neighbor.

Каждый файл создан отдельным запросом по точному шаблону:

```text
Use case: stylized-concept. Asset type: a single seamless tileable square voxel game block texture called {name}. Material: {material}. Strict orthographic flat material surface filling entire square edge to edge. Original crisp pixel art, chunky 32 by 32 pixel design scaled up with nearest neighbor. Limited palette 8 to 12 colors, strong readable texture at small size. No perspective, no 3D cube, no frame or margins, no letters, no captions, no watermark. Opaque image. All four edges must tile seamlessly.
```

| name (файл `src/imgs/{name}.png`) | material |
| --- | --- |
| hell_soil | dark plum volcanic ash soil covered with crimson fungal threads and a few orange ember flecks |
| hell_stone | porous rough burnt red volcanic rock with dark maroon fissures |
| hell_basalt | charcoal basalt with angular dark blue gray cracks |
| hell_log | vertical bark of an infernal tree, dark purple brown with thin glowing amber vertical cracks |
| hell_leaves | dense crimson red and wine purple fungal leaves, with scattered dark orange tips, fully opaque |
| ember_crystal | clustered amber orange glowing crystals embedded in dark volcanic rock |
| hell_lava | molten bright orange and yellow lava with dark red flowing angular eddies |
| portal_frame | ancient nearly black purple stone masonry with small golden rune-like geometric lines, no letters |
| forest_portal | swirling luminous violet and magenta energy, square concentric magical ripples |
