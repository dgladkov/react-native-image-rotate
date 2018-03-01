require 'json'
version = JSON.parse(File.read('package.json'))["version"]

Pod::Spec.new do |s|

  s.name            = "RNImageRotate"
  s.version         = version
  s.homepage        = "https://github.com/dgladkov/react-native-image-rotate"
  s.summary         = "Rotate images from your react-native JavaScript code"
  s.license         = "MIT"
  s.author          = { "Dmitry Gladkov" => "dmitry.gladkov@gmail.com" }
  s.platform        = :ios, "7.0"
  s.source          = { :git => "https://github.com/dgladkov/react-native-image-rotate.git", :tag => s.version.to_s }
  s.source_files    = "RNImageRotate/*.{h,m}"
  s.preserve_paths  = "**/*.js"

  s.dependency 'React'
end
