# Текстуры ступенек и поворачивателя

Созданы встроенным инструментом `image_gen` (навык imagegen), без CLI/API fallback. Итоговые файлы скопированы в проект без изменений; исходные изображения сохранены в каталоге generated_images.

- `src/imgs/stairs.png` — непрозрачная текстура светлой каменной кладки для ступенек.
- `src/imgs/rotator.png` — значок поворачивателя: ключ с золотой стрелкой и прозрачным фоном.

## Промпт ступенек

Use case: stylized-concept. Asset type: seamless square voxel game material texture. Create a single flat full-bleed tile of warm pale sandstone masonry for stone stairs in a retro pixel-art block building game. Orthographic surface only, NOT a picture of a staircase, not an icon or 3D cube. Chunky 16-by-16-pixel visual grid enlarged uniformly, crisp nearest-neighbor square pixels, limited palette of 5 sand beige and brown shades, two horizontal rows of staggered stone bricks, subtle sparse chips, even lighting without directional shadows. Seamless edges. Opaque background, square composition, no padding, no text, no border, no watermark. The image will be used directly as a repeating texture on the tread, riser and sides of stair blocks.

## Промпт поворачивателя

Use case: stylized-concept. Asset type: transparent pixel-art inventory icon for a voxel game tool called Rotator. Create one compact, instantly readable tool icon: a short steel wrench with a dark wooden handle pointing diagonally up-right, surrounded by one bold golden clockwise curved arrow showing a quarter turn. Retro voxel-game pixel art with a coarse 32-by-32 pixel visual grid enlarged uniformly, hard square pixel edges, dark outline, limited palette, no antialiasing, no gradient. Centered icon filling most of a square canvas with small transparent margin. Actual transparent alpha background, not a checkerboard. No letters, numbers, labels, text or watermark. Must stay legible at 32 pixels in a hotbar.
