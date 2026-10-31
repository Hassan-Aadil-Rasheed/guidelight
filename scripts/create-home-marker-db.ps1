# Rebuild the two supported Home references without changing the original marker-test database.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$markerAssetDirectory = Join-Path $projectRoot 'app/src/main/assets/indoor'
$imageList = Join-Path $projectRoot '.tools/ar-research/home-images.txt'
@(
    "home-earth-18cm|$markerAssetDirectory/home-marker.jpg|0.18"
    "home-earth-personal-18cm|$markerAssetDirectory/home-marker-personal.jpg|0.18"
) | Set-Content -LiteralPath $imageList -Encoding ascii
& (Join-Path $projectRoot '.tools/ar-research/arcoreimg.exe') build-db "--input_image_list_path=$imageList" "--output_db_path=$markerAssetDirectory/home-markers.imgdb"
if ($LASTEXITCODE -ne 0) { throw 'Home marker database build failed.' }
Move-Item -LiteralPath "$markerAssetDirectory/home-markers.imgdb-imglist.txt" -Destination (Join-Path $projectRoot '.tools/ar-research/home-db-generated-list.txt') -Force
