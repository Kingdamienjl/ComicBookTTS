# 🌟 Animated Digital Pet Interface

An advanced, interactive digital pet application featuring real-time animations, dynamic facial expressions, and responsive behaviors. Transform any uploaded image into a living, breathing digital companion!

![Pet Demo](https://img.shields.io/badge/Status-Live%20Demo-brightgreen)
![Python](https://img.shields.io/badge/Python-3.7+-blue)
![GUI](https://img.shields.io/badge/GUI-Tkinter-orange)

## ✨ Features

### 🎭 **Advanced Animation System**
- **Real-time Movement**: Pets move independently across the screen with fluid, natural motions
- **Facial Expressions**: Dynamic expressions showcasing 10 different emotions:
  - 😊 Happy - Bouncing with sparkles
  - 😢 Sad - Drooping with reduced opacity
  - 🤩 Excited - Spinning with hearts
  - 🤔 Curious - Head tilting and investigating
  - 😴 Sleepy - Gentle swaying with Z's
  - 🎾 Playful - Wiggling with musical notes
  - 😠 Angry - Intense movements
  - 😲 Surprised - Jumping animations
  - 😌 Content - Peaceful breathing
  - 😑 Bored - Minimal movement

### 🎮 **Interactive Features**
- **Click Interactions**: Pet responds to mouse clicks with surprise animations
- **Drag to Pet**: Smooth petting interactions increase happiness
- **Feed System**: Reduce hunger and boost happiness
- **Play Mode**: Engage in playful activities
- **Custom Image Upload**: Use your own pet images!

### 🧠 **AI Personality System**
- **Dynamic Stats**: Happiness, Energy, Hunger, Attention, Playfulness
- **Autonomous Behavior**: Pet acts independently when not interacted with
- **Emotion-Based Responses**: Reactions change based on current emotional state
- **Memory System**: Pet remembers recent interactions

### 🎨 **Visual Effects**
- **Particle Systems**: Sparkles, hearts, musical notes, and Z's
- **Smooth Transformations**: Scaling, rotation, and opacity changes
- **Starfield Background**: Animated cosmic environment
- **Real-time Rendering**: 60 FPS smooth animations

## 🚀 Quick Start

### Prerequisites
```bash
pip install pillow
```

### Running the Application
```bash
python animated_pet_main.py
```

## 🎯 How to Use

### 1. **Basic Interaction**
- **Click anywhere** on the canvas to get your pet's attention
- **Drag your mouse** across the pet to give it affection
- Watch as your pet **moves autonomously** around the screen

### 2. **Care Actions**
- **🍎 Feed**: Reduces hunger, increases happiness
- **🎾 Play**: Boosts playfulness and happiness (costs energy)
- **❤️ Pet**: Direct affection increases happiness and attention
- **📷 Upload Image**: Replace the default pet with your own image

### 3. **Understanding Your Pet**
- **Stats Display**: Monitor Happiness, Energy, Hunger, Attention, Playfulness
- **Color Coding**: 
  - 🟢 Green (70-100%): Excellent
  - 🟡 Yellow (40-69%): Good
  - 🔴 Red (0-39%): Needs attention
- **Status Messages**: Real-time emotion display

### 4. **Custom Pet Images**
1. Click **"📷 Upload Image"**
2. Select any image file (PNG, JPG, GIF, BMP)
3. Image will be automatically resized and optimized
4. Your custom pet will immediately start animating!

## 🏗️ Technical Architecture

### Core Components

#### `animated_pet_core.py`
- **AnimatedPet Class**: Main pet logic and state management
- **EmotionState Enum**: 10 different emotional states
- **AnimationFrame**: Frame-by-frame animation data
- **PetStats**: Dynamic statistics system

#### `animated_pet_gui.py`
- **AnimatedPetGUI Class**: Main interface and rendering
- **Real-time Animation Loop**: 60 FPS rendering system
- **Image Processing**: PIL-based transformations
- **Event Handling**: Mouse and keyboard interactions

#### `animated_pet_main.py`
- **Application Entry Point**: Startup and error handling
- **Feature Overview**: User guidance and tips

### Animation System
```python
# Example animation frame
AnimationFrame(
    scale_x=1.1,        # Horizontal scaling
    scale_y=0.9,        # Vertical scaling  
    rotation=15,        # Rotation in degrees
    offset_x=10,        # X position offset
    offset_y=-5,        # Y position offset
    opacity=0.8,        # Transparency
    expression_modifier="glow"  # Special effects
)
```

### Emotion-Behavior Mapping
| Emotion | Trigger | Animation | Effects |
|---------|---------|-----------|---------|
| Happy | Happiness > 80% | Bouncing | ✨ Sparkles |
| Excited | High energy + happiness | Spinning | 💖 Hearts |
| Sleepy | Energy < 20% | Slow swaying | 💤 Z's |
| Playful | High playfulness | Wiggling | ♪ Music notes |
| Curious | Recent interaction | Head tilting | 👀 Attention |

## 🎨 Customization

### Adding New Animations
```python
# In animated_pet_core.py
sequences["new_emotion"] = [
    AnimationFrame(scale_y=1.0, offset_y=0),
    AnimationFrame(scale_y=1.2, offset_y=-10),
    AnimationFrame(scale_y=0.8, offset_y=5),
    AnimationFrame(scale_y=1.0, offset_y=0),
]
```

### Custom Visual Effects
```python
# In animated_pet_gui.py - add_emotion_effects()
elif emotion == EmotionState.NEW_EMOTION:
    # Add custom visual effect
    self.canvas.create_text(
        x, y-30, text="🌟", 
        fill="gold", font=("Arial", 16),
        tags="pet"
    )
```

## 🔧 Performance Optimization

- **Efficient Rendering**: Only redraws changed elements
- **Memory Management**: Automatic cleanup of animation frames
- **Threading**: Separate animation and UI threads
- **Image Caching**: Optimized image transformations

## 📊 Statistics System

The pet maintains five core statistics that influence behavior:

- **Happiness** (0-100%): Overall mood and contentment
- **Energy** (0-100%): Activity level and responsiveness  
- **Hunger** (0-100%): Need for feeding (higher = hungrier)
- **Attention** (0-100%): Desire for interaction
- **Playfulness** (0-100%): Interest in games and activities

### Stat Interactions
- Hunger increases over time
- Energy decreases with activity
- Low energy/high hunger reduces happiness
- Interactions boost relevant stats

## 🎮 Gameplay Tips

1. **Keep your pet happy** by maintaining balanced stats
2. **Regular feeding** prevents hunger from affecting mood
3. **Play sessions** boost happiness but consume energy
4. **Let your pet rest** when energy is low
5. **Frequent interaction** maintains attention levels
6. **Upload fun images** to personalize your experience

## 📁 File Structure

```
animated_pet/
├── animated_pet_main.py          # Application entry point
├── animated_pet_core.py          # Pet logic and animations
├── animated_pet_gui.py           # GUI and rendering
├── animated_pet_requirements.txt # Dependencies
├── image_converter.py            # Image processing utilities
└── README.md                     # This file
```

## 🤝 Contributing

Want to add new features? Here are some ideas:

- **New Emotions**: Add more emotional states and animations
- **Sound Effects**: Audio feedback for interactions
- **Mini-Games**: Interactive games for the pet
- **Customization**: More visual customization options
- **AI Behaviors**: Advanced AI-driven personality traits
- **Multiplayer**: Multiple pets interacting

## 🐛 Troubleshooting

### Common Issues

**"No module named 'PIL'"**
```bash
pip install pillow
```

**Pet not moving**
- Check that the application window has focus
- Ensure no errors in the console output

**Image upload fails**
- Verify image file format (PNG, JPG, GIF, BMP)
- Check file permissions
- Try a smaller image file

**Low performance**
- Close other applications
- Reduce window size if needed
- Check system resources

## 📜 License

This project is open source and available under the MIT License.

## 🌟 Acknowledgments

- Built with Python and Tkinter
- Image processing powered by Pillow (PIL)
- Inspired by classic virtual pet games
- Designed for modern interactive experiences

---

**Enjoy your new animated digital companion!** 🎉

*Watch as your pet comes to life with personality, emotions, and endless entertainment.*