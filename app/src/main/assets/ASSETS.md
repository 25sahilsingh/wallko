# Bundled assets

All `.tflite` files were checked: bytes 4–8 are the TFLite flatbuffer identifier `TFL3`. Tensor shapes were read straight from the flatbuffer (`Model → SubGraph[0] → inputs/outputs`).

## Models (`models/`)

| File | Source URL | Licence | Size (bytes) | sha256 | Inputs → Outputs | Notes |
|---|---|---|---|---|---|---|
| `aiy_plants_v1.tflite` | https://www.kaggle.com/api/v1/models/google/aiy/tfLite/vision-classifier-plants-v1/1/download (tar.gz holding `1.tflite`; Kaggle `google/aiy/tfLite/vision-classifier-plants-v1` v1, formerly TF Hub `google/aiy/vision/classifier/plants_V1`) | Apache 2.0 | 5,111,437 | `bf3f8319881b2b4cdb8bd1910fe1e88a18bb25bd7fea7ee830ee702b6810ac9f` | `module/hub_input/images_uint8` [1,224,224,3] uint8 → `module/prediction` [1,2102] uint8 | Has metadata. The appended zip contains `0-labels.txt` (2102 lines, scientific names, e.g. `Betula lenta`) and `0-labels-en.txt` (2102 lines, e.g. `Betula lenta (Sweet birch)`). |
| `style_predict.tflite` | https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/style_transfer/android/magenta_arbitrary-image-stylization-v1-256_int8_prediction_1.tflite | Apache 2.0 | 2,828,838 | `af6ad4b2e7aeba0675f32636082ab915ced5375229a3f8aff7e714c6213f5ed2` | `style_image` [1,256,256,3] float32 → `mobilenet_conv/Conv/BiasAdd` [1,1,1,100] float32 | Magenta arbitrary-image-stylization-v1-256, int8-quantised weights, float I/O (pixels in 0..1). Has an empty metadata zip. |
| `style_transfer.tflite` | https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/style_transfer/android/magenta_arbitrary-image-stylization-v1-256_int8_transfer_1.tflite | Apache 2.0 | 284,398 | `7a1550643cf034a4d813c0aa276976cd15da4141b4f1ec3631db1d0d9c8e2cd1` | `content_image` [1,384,384,3] float32 + `mobilenet_conv/Conv/BiasAdd` [1,1,1,100] float32 → `transformer/expand/conv3/conv/Sigmoid` [1,384,384,3] float32 | Int8-quantised weights, float I/O. Output is a sigmoid in 0..1. Input order: content is input 0, style vector is input 1. |

Total model size is 8,224,673 bytes (about 7.8 MiB).

## Style images (`styles/`)

| File | Source | Licence | Size (bytes) | sha256 | Format | Notes |
|---|---|---|---|---|---|---|
| `watercolour.jpg` | Generated procedurally for Wallko (Pillow script, not in repo) | Original work, no third-party rights (dedicated CC0) | 21,566 | `3ee66c2e679771eb6d8d3c462501f304c169a8872431a8704c1cf23afdff7830` | 384×384 JPEG | Soft overlapping pigment blobs with darker wet edges and paper grain. |
| `flat_pastel.jpg` | Generated procedurally for Wallko | Original work (CC0) | 18,515 | `74a15fc3a20959c2405274483846df33b819ad9ad8f164d83600d171ba40d808` | 384×384 JPEG | Flat-colour meadow scene: sky, sun, clouds, hills, flowers. |
| `crayon.jpg` | Generated procedurally for Wallko | Original work (CC0) | 59,558 | `0a2369a548db331254a4f23a9c26e9949be2fab53af6d81c6d758bcdd955552d` | 384×384 JPEG | Hatched waxy strokes masked by paper-tooth noise. |

All three use only the Sunny Meadow palette: #FFF8E7, #8ED6FF, #7ED957, #FFD93D, #FFB5A7, #FF6F91, #C3B1E1. The prediction network takes 256×256 input, so resize the images before inference.

## Attribution

AIY Vision plants classifier and Magenta arbitrary image stylization models: Copyright Google LLC, licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0). Bundled unmodified.
