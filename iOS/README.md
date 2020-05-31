
The Stethogram iOS app uses AudioKit https://cocoapods.org/pods/AudioKit   
AudioKit is not part of this repository.   
    
Prerequisite:    
- Make sure you have CocoaPods installed https://guides.cocoapods.org/using/getting-started.html    
    pod --version  
- Before you open the Xcode project, install AudioKit. Quite Xcode, then   
    cd stethogram   
    pod install   
   
Build error:    
- If you see a build error "Framework not found Pods_stethogram", do the following   
- In Xcode, open the "stethogram" project settings.   
- in the "General" tab, scroll down to find "Frameworks, Libraries, and Embedded Content" section  
- Delete the entry "Pods_stethogram.framework"   
- Build again   
   
   
