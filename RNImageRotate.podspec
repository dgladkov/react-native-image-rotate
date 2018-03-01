require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|

  s.name            = "RNImageRotate"
  s.version         = package['version']
  s.summary         = package['description']
  s.description     = package['description']
  s.homepage        = "https://github.com/dgladkov/react-native-image-rotate"
  s.license         = package['license']
  s.author          = { "Dmitry Gladkov" => "dmitry.gladkov@gmail.com" }
  s.platform        = :ios, "7.0"
  s.source          = { :git => "https://github.com/dgladkov/react-native-image-rotate.git", :tag => s.version.to_s }
  s.source_files    = "RNImageRotate/*.{h,m}"
  s.preserve_paths  = "**/*.js"

  s.dependency 'React'
end
